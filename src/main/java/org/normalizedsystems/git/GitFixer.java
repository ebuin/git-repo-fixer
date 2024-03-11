package org.normalizedsystems.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.URIish;
import picocli.CommandLine;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings({"java:S106", "java:S112", "CallToPrintStackTrace"})
public class GitFixer {

  public static void main(String[] args) {
    Params params = new Params();
    new CommandLine(params).parseArgs(args);

    try {
      processGitRepos(params);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static void processGitRepos(Params params) throws IOException {
    GitSshSetup.setupSsh(params);
    testConnection();
    List<Path> gitRepoLocations = findGitRepositories(params);
    System.out.println("Found (" + gitRepoLocations.size() + ") git repositories");
    for (Path gitRepoLocation : gitRepoLocations) {
      try {
        processGitRepository(gitRepoLocation, params);
      } catch (IOException | URISyntaxException e) {
        System.err.println("Failed to process " + gitRepoLocation);
        e.printStackTrace();
      }
    }
  }

  private static void testConnection() {
    try {
      Git.lsRemoteRepository()
          .setRemote("git@github.com:normalizedsystems/nsx-parent.git")
          .setTransportConfigCallback(transport -> {
            SshTransport sshTransport = (SshTransport) transport;
            sshTransport.setSshSessionFactory(GitSshSetup.sessionFactory);
          })
          .call();
    } catch (Exception e) {
      System.err.println("Access to github failed");
      e.printStackTrace();
      System.exit(1);
    }
  }

  private static List<Path> findGitRepositories(Params params) throws IOException {
    Path rootDirectory = params.rootDirectory;
    List<Path> gitRepoLocations = new ArrayList<>();
    Files.walkFileTree(rootDirectory, new SimpleFileVisitor<>() {

      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        FileVisitResult baseResult = super.preVisitDirectory(dir, attrs);
        if (dir.getFileName().toString().equals("target")) {
          return FileVisitResult.SKIP_SUBTREE;
        }
        if (Files.exists(dir.resolve(".git"))) {
          gitRepoLocations.add(dir);
          return FileVisitResult.SKIP_SUBTREE;
        }
        return baseResult;
      }
    });
    return gitRepoLocations;
  }

  private static void processGitRepository(Path gitRepoLocation, Params params) throws IOException, URISyntaxException {
    try (Git git = Git.open(gitRepoLocation.toFile())) {
      List<Remote> remotes = getDirtyRemotes(git, params);
      if (remotes.isEmpty()) {
        System.out.println("-- " + git.getRepository().getDirectory() + " is clean");
        return;
      }
      System.out.println("> Processing " + gitRepoLocation);
      System.out.println("  Remotes to process:");
      for (Remote remote : remotes) {
        System.out.println("  - " + remote.name() + ": " + remote.uri());
      }
      System.out.println("  Changing remotes to " + params.to);
      for (Remote remote : remotes) {
        String newUri = remote.uri().replace(params.from, params.to);
        Remote newRemote = new Remote(remote.name(), newUri);
        if (testRemote(newRemote)) {
          setRemote(git, newRemote);
        }
      }
    }
  }

  private static boolean testRemote(Remote remote) {
    try {
      Git.lsRemoteRepository()
          .setRemote(remote.uri())
          .setTransportConfigCallback(transport -> {
            SshTransport sshTransport = (SshTransport) transport;
            sshTransport.setSshSessionFactory(GitSshSetup.sessionFactory);
          })
          .call();
      return true;
    } catch (InvalidRemoteException e) {
      System.err.println("    | github remote does not exist, keeping current URI");
      return false;
    } catch (Exception e) {
      System.err.println("    | Polling remote failed, keeping current URI (" + e.getMessage() + ")");
      return false;
    }
  }

  private static List<Remote> getDirtyRemotes(Git git, Params params) {
    try {
      ArrayList<Remote> remotes = new ArrayList<>();
      List<RemoteConfig> remoteConfigs = git.remoteList().call();
      for (RemoteConfig remoteConfig : remoteConfigs) {
        String remoteName = remoteConfig.getName();
        for (URIish uri : remoteConfig.getURIs()) {
          if (uri.getHost().contains(params.from)) {
            remotes.add(new Remote(remoteName, uri.toString()));
            break;
          }
        }
      }
      return remotes;
    } catch (Exception e) {
      System.err.println("Failed to process " + git.getRepository().getDirectory());
      e.printStackTrace();
      return Collections.emptyList();
    }
  }

  private static void setRemote(Git git, Remote remote) throws URISyntaxException {
    System.out.println("  - changing " + remote.name() + " to " + remote.uri());
    try {
      git.remoteSetUrl()
          .setRemoteName(remote.name())
          .setRemoteUri(new URIish(remote.uri()))
          .call();
    } catch (GitAPIException e) {
      System.out.println("    | cannot change remote, " + e.getMessage());
    }
  }

}

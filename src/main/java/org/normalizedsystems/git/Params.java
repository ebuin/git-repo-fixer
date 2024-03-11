package org.normalizedsystems.git;

import picocli.CommandLine;

import java.nio.file.Path;

public class Params {

  @CommandLine.Option(names = "--dir")
  public Path rootDirectory;

  @CommandLine.Option(names = "--key")
  public Path sshKey;

  @CommandLine.Option(names = "--pas", defaultValue = "")
  public String sshPassphrase;

  public String from = "bitbucket.org";
  public String to = "github.com";

}

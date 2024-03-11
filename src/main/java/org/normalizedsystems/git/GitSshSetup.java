package org.normalizedsystems.git;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.ssh.jsch.JschConfigSessionFactory;
import org.eclipse.jgit.util.FS;

public class GitSshSetup {

  private static boolean configured = false;

  public static JschConfigSessionFactory sessionFactory;

  public static synchronized void setupSsh(Params params) {
    if (configured) return;

    sessionFactory = createSessionFactory(params);
    SshSessionFactory.setInstance(sessionFactory);
    configured = true;
  }

  private static JschConfigSessionFactory createSessionFactory(Params params) {
    return new JschConfigSessionFactory() {
      @Override
      protected JSch createDefaultJSch(FS fs) throws JSchException {
        JSch defaultJSch = super.createDefaultJSch(fs);
        defaultJSch.addIdentity(params.sshKey.toString(), params.sshPassphrase);
        return defaultJSch;
      }
    };
  }

}

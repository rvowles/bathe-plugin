package com.bluetrainsoftware.maven.bathe

import groovy.transform.CompileStatic
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.plugins.annotations.ResolutionScope

/**
 * author: Richard Vowles - http://gplus.to/RichardVowles
 */
@CompileStatic
@Mojo(name = "sponge", requiresProject = true, requiresDependencyResolution = ResolutionScope.TEST, defaultPhase = LifecyclePhase.TEST)
class SpongeTestMojo extends BaseBatheMojo {

  @Parameter(property = 'run.mainClass')
  public String mainClass

  @Parameter(property = 'run.args')
  public String args

  @Parameter(property = 'run.xargs')
  public String xArgs

  @Parameter(property = 'process.wait')
  Boolean processWait

  public static Process runner
  public static Integer returnCode

  protected void log() {
    getLog().info("bathe pre integration test: extension ${extension()}, main class ${mainClass}")
  }

  @Override
  void execute() throws MojoExecutionException, MojoFailureException {
    if (!mainClass) {
      getLog().info("bathe-sponge: No main class, skipping")
      return // skip test
    }

    log()

    File execFile = generatedFile

    List<String> passedArguments = []

    if (xArgs) {
      passedArguments.addAll(xArgs.replace('{}', execFile.absolutePath).split(' '))
    }

    passedArguments.add(execFile.absolutePath)
    passedArguments.add(mainClass)

    if (args) {
      passedArguments.addAll(args.replace('{}', execFile.absolutePath).split(' '))
    }

    createJavaProcess(passedArguments)

    if (processWait)
      new MonitorProcess().run()
    else
      new Thread(new MonitorProcess()).start()
  }

  class MonitorProcess implements Runnable {

    @Override
    void run() {
      BufferedReader processOutputReader = null;
      try {
        processOutputReader = new BufferedReader(new InputStreamReader(runner.inputStream))

        for (String line = processOutputReader.readLine(); line != null; line = processOutputReader.readLine()) {
          getLog().info("bathe-sponge: " + line);
        }

        returnCode = runner.waitFor()

        if (returnCode)
          getLog().info("bath-sponge: process result is ${returnCode}")
        else
          getLog().info("bath-sponge: was successful!")

      } catch (IOException e) {
        throw new MojoExecutionException("There was an error reading the output from Java.", e);

      } catch (InterruptedException e) {
        throw new MojoExecutionException("The Java process was interrupted.", e);

      } finally {
        processOutputReader.close()
      }
    }
  }

  protected void createJavaProcess(List<String> args) {
    ProcessBuilder builder

    if (File.separator == '\\') // Windows
      builder = new ProcessBuilder("cmd", "/C", "java")
    else
      builder = new ProcessBuilder("java");

    List<String> command = builder.command();

    command.add("-jar")
    command.addAll(args);

    builder.redirectErrorStream(true);

    try {
      getLog().info("bathe-sponge : ${command.join(' ')}")

      builder.redirectErrorStream(true)
      runner = builder.start();

    } catch (IOException e) {
      throw new MojoExecutionException("There was an error executing Java.", e);
    }
  }
}

package com.bluetrainsoftware.maven.bathe

import groovy.transform.CompileStatic
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException

/**

 * author: Richard Vowles - http://gplus.to/RichardVowles
 */
@CompileStatic
class RinseMojo extends BaseBatheMojo {
  @Override
  void execute() throws MojoExecutionException, MojoFailureException {
    if (SpongeTestMojo.runner) {
      getLog().info("bathe-rinse: shutting down process!")
      SpongeTestMojo.runner.destroy()
    }
  }
}

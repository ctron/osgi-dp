/*******************************************************************************
 * Copyright (c) 2016, 2018 Jens Reimann
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jens Reimann <jreimann@redhat.com> - initial API and implementation
 *******************************************************************************/
package de.dentrassi.maven.osgi.dp;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.Manifest;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Build this project as an OSGi distribution package.
 * <p>
 * <strong>Note:</strong> Use the packaging type <code>dp</code> instead of
 * directly configuring this mojo.
 * </p>
 * <p>
 * Be sure to add this plugin as an extension:
 * </p>
 * {@code <plugins>
    <plugin>
        <groupId>de.dentrassi.maven</groupId>
        <artifactId>osgi-dp</artifactId>
        <extensions>true</extensions>
    </plugin>
</plugins>}
 * <p>
 * This project takes the dependencies of this project and builds an OSGi DP
 * from it.
 * </p>
 *
 * @author Jens Reimann
 * @since 0.3.0
 */
@Mojo(name = "package", defaultPhase = LifecyclePhase.PACKAGE, requiresProject = true, requiresDependencyResolution = ResolutionScope.RUNTIME, requiresDependencyCollection = ResolutionScope.RUNTIME, threadSafe = false)
public class PackageMojo extends AbstractDpMojo {

    @Override
    protected void attach(final Path out) {
        this.project.getArtifact().setFile(out.toFile());
    }

    @Override
    protected void fillFromDependencies(final Manifest manifest, final Map<String, File> files)
            throws IOException, MojoExecutionException {

        super.fillFromDependencies(manifest, files);

        for (final Artifact art : this.project.getDependencyArtifacts()) {
            File file = art.getFile();
            if (file == null) {
                getLog().info("Skipping " + art + " because it has no file");
                return;
            }
            processArtifact(manifest, files, file);
        }
    }
}

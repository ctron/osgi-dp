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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.Manifest;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Build this project as an OSGi distribution package.
 * <p>
 * This project takes the main attachment and the dependencies of this project
 * and builds an OSGi DP from it. By default only direct {@code compile} and
 * {@code runtime} dependencies will be added to the package. All extra
 * dependencies will be included.
 * </p>
 *
 * @author Jens Reimann
 * @since 0.4.0
 */
@Mojo(name = "bundle", defaultPhase = LifecyclePhase.PACKAGE, requiresProject = true, requiresDependencyResolution = ResolutionScope.RUNTIME, requiresDependencyCollection = ResolutionScope.RUNTIME, threadSafe = false)
public class BundleMojo extends AbstractDpMojo {

    /**
     * Whether or not the resulting DP should be attached to the project output.
     */
    @Parameter(defaultValue = "true")
    protected boolean attach = true;

    /**
     * Whether or not to fail if the main artifact is missing
     */
    @Parameter(defaultValue = "true")
    protected boolean failOnMissingMainArtifact = true;

    /**
     * Allows to ignore the project dependencies and only use the extra
     * dependencies.
     */
    @Parameter(defaultValue = "false")
    protected boolean ignoreProjectDependecies = false;

    /**
     * The set of scopes for dependencies which will be included in the DP
     */
    @Parameter(defaultValue = "compile,runtime")
    protected Set<String> includedScopes = new HashSet<>();

    @Override
    protected void attach(final Path out) {
        if (!this.attach) {
            return;
        }

        this.projectHelper.attachArtifact(this.project, "dp", out.toFile());
    }

    @Override
    protected void fillFromDependencies(final Manifest manifest, final Map<String, File> files)
            throws IOException, MojoExecutionException {

        super.fillFromDependencies(manifest, files);

        if (!this.ignoreProjectDependecies) {
            for (final Artifact art : this.project.getDependencyArtifacts()) {

                final String scope = art.getScope();
                if (this.includedScopes.contains(scope)) {
                    File file = art.getFile();
                    if (file == null) {
                        getLog().info("Skipping " + art + " because it has no file");
                        return;
                    }
                    processArtifact(manifest, files, file);
                }
            }
        }

        processMainArtifact(manifest, files);
    }

    private void processMainArtifact(final Manifest manifest, final Map<String, File> files)
            throws MojoExecutionException, IOException {

        final File artifact = this.project.getArtifact().getFile();

        if (artifact == null || !artifact.isFile()) {
            if (this.failOnMissingMainArtifact) {
                throw new MojoExecutionException(
                        "The main artifact is missing. You may disable this error by setting 'failOnMissingMainArtifact' to 'true'");
            }
        }

        if (artifact != null && artifact.isFile()) {
            processArtifact(manifest, files, artifact);
        }
    }
}

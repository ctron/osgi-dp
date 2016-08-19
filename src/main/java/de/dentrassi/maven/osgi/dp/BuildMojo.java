/*******************************************************************************
 * Copyright (c) 2016 Jens Reimann.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jens Reimann <jreimann@redhat.com> - initial API and implementation
 *******************************************************************************/
package de.dentrassi.maven.osgi.dp;

import static java.lang.String.format;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.eclipse.tycho.ArtifactDescriptor;
import org.eclipse.tycho.ReactorProject;
import org.eclipse.tycho.core.ArtifactDependencyVisitor;
import org.eclipse.tycho.core.ArtifactDependencyWalker;
import org.eclipse.tycho.core.DependencyResolver;
import org.eclipse.tycho.core.FeatureDescription;
import org.eclipse.tycho.core.PluginDescription;
import org.eclipse.tycho.core.TychoProject;

import com.google.common.io.ByteStreams;

/**
 * Build an OSGi distribution package from an Eclipse Feature
 *
 * @author Jens Reimann
 */
@Mojo(name = "build", defaultPhase = LifecyclePhase.PACKAGE, requiresProject = true, requiresDependencyResolution = ResolutionScope.COMPILE, requiresDependencyCollection = ResolutionScope.COMPILE, threadSafe = false)
public class BuildMojo extends AbstractMojo {

    /**
     * The maven project
     */
    @Parameter(property = "project", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Parameter(defaultValue = "true")
    protected boolean useQualifiedFilename = true;

    @Parameter(defaultValue = "true")
    protected boolean attach = true;

    @Parameter(defaultValue = "false", property = "osgi-dp.skip")
    protected boolean skip = false;

    @Component
    private MavenProjectHelper projectHelper;

    @Component
    private DependencyResolver dependencyResolver;

    @Component(role = TychoProject.class)
    private Map<String, TychoProject> projectTypes;

    protected ArtifactDependencyWalker lookupDependencyWalker() {
        final String packaging = this.project.getPackaging();

        final TychoProject facet = this.projectTypes.get(packaging);
        if (facet == null) {
            throw new IllegalStateException(format("Unknown packaging '%s'", packaging));
        }

        return facet.getDependencyWalker(this.project);
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (this.skip) {
            return;
        }

        final String dpVersion = makeVersion(true);

        getLog().info("Building DP - Version: " + dpVersion);

        final Manifest dpmf = new Manifest();
        dpmf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1");
        dpmf.getMainAttributes().putValue("DeploymentPackage-SymbolicName", this.project.getArtifactId());
        dpmf.getMainAttributes().putValue("DeploymentPackage-Version", dpVersion);

        try {
            final Map<String, File> files = new HashMap<>();

            fillFromDependencies(dpmf, files);

            final String dpName = String.format("%s_%s.dp", this.project.getArtifactId(),
                    makeVersion(this.useQualifiedFilename));
            final Path out = Paths.get(this.project.getBuild().getDirectory(), dpName);

            getLog().info("Writing to: " + out);

            Files.createDirectories(out.getParent());

            try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(out), dpmf)) {

                for (final Map.Entry<String, File> entry : files.entrySet()) {

                    final Path p = entry.getValue().toPath();
                    final JarEntry je = new JarEntry(entry.getKey());

                    je.setSize(Files.size(p));
                    je.setLastModifiedTime(Files.getLastModifiedTime(p));

                    jar.putNextEntry(je);
                    try (InputStream in = Files.newInputStream(p)) {
                        ByteStreams.copy(in, jar);
                    }
                }
            }

            if (this.attach) {
                this.projectHelper.attachArtifact(this.project, "dp", out.toFile());
            }

        } catch (final IOException e) {
            throw new MojoFailureException("Failed to process", e);
        }
    }

    private void fillFromDependencies(final Manifest dpmf, final Map<String, File> files) throws IOException {

        final ArtifactDependencyWalker dw = lookupDependencyWalker();
        dw.walk(new ArtifactDependencyVisitor() {
            @Override
            public boolean visitFeature(final FeatureDescription feature) {
                return true;
            }

            @Override
            public void visitPlugin(final PluginDescription plugin) {
                try {
                    considerArtifact(dpmf, files, plugin);
                } catch (final IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    private void considerArtifact(final Manifest dpmf, final Map<String, File> files, final ArtifactDescriptor art)
            throws IOException {
        getLog().debug(format("Considering artifact: %s", art));

        if (!"eclipse-plugin".equalsIgnoreCase(art.getKey().getType())) {
            getLog().debug(format("Not a JAR file -> %s", art.getKey().getType()));
            return;
        }

        final File location;
        final ReactorProject p = art.getMavenProject();
        if (p != null) {
            location = p.getArtifact();
        } else {
            location = art.getLocation();
        }

        if (location == null) {
            getLog().warn(format("Unable to locate artifact: %s", art));
        }

        if (!location.isFile()) {
            getLog().warn(format("Location '%s' is not a file", location));
            return;
        }

        try (JarFile jar = new JarFile(location)) {
            final Manifest mf = jar.getManifest();
            if (mf == null) {
                getLog().debug("No Manifest");
                return;
            }

            String bsn = mf.getMainAttributes().getValue("Bundle-SymbolicName");
            if (bsn == null) {
                getLog().debug("No BSN");
                return;
            }
            if (bsn != null) {
                bsn = bsn.split(";", 2)[0];
            }

            final String version = mf.getMainAttributes().getValue("Bundle-Version");

            final String fn = String.format("%s_%s.jar", bsn, version);

            final Attributes attrs = new Attributes();
            attrs.putValue("Bundle-SymbolicName", bsn);
            attrs.putValue("Bundle-Version", version);
            dpmf.getEntries().put(fn, attrs);

            getLog().info(format("Added: %s:%s", bsn, version));

            files.put(fn, location);
        }
    }

    private String makeVersion(final boolean qualified) {
        if (qualified) {
            final ReactorProject rp = (ReactorProject) this.project.getContextValue(ReactorProject.CTX_REACTOR_PROJECT);
            if (rp == null) {
                throw new IllegalStateException(
                        "Failed to get qualified project version. On non-tycho projects set 'useQualifiedFilename' to 'false'");
            }
            return rp.getExpandedVersion();
        } else {
            return this.project.getVersion();
        }
    }
}

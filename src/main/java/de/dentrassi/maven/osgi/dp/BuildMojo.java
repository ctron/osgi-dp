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
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
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
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.tycho.ReactorProject;
import org.eclipse.tycho.core.DependencyResolver;
import org.eclipse.tycho.core.TychoProject;
import org.osgi.framework.Version;

import com.google.common.io.ByteStreams;

import de.dentrassi.maven.osgi.dp.internal.ArtifactWalker;
import de.dentrassi.maven.osgi.dp.internal.ProjectWalker;
import de.dentrassi.maven.osgi.dp.internal.TychoWalker;

/**
 * Build an OSGi distribution package
 *
 * @author Jens Reimann
 */
@Mojo(name = "build", defaultPhase = LifecyclePhase.PACKAGE, requiresProject = true, requiresDependencyResolution = ResolutionScope.RUNTIME, requiresDependencyCollection = ResolutionScope.RUNTIME, threadSafe = false)
public class BuildMojo extends AbstractMojo {

    private final static DateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyyMMddHHmmss");

    /**
     * The maven project
     */
    @Parameter(property = "project", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Component
    private RepositorySystem repoSystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true, required = true)
    private RepositorySystemSession repositorySession;

    @Parameter(defaultValue = "false")
    protected boolean useQualifiedFilename = false;

    @Parameter(defaultValue = "true")
    protected boolean attach = true;

    @Parameter(defaultValue = "false", property = "osgi-dp.skip")
    protected boolean skip = false;

    @Component
    private MavenProjectHelper projectHelper;

    @Component
    private DependencyResolver dependencyResolver;

    private final Set<String> tychoWalkerProjects = new HashSet<>(Arrays.asList("eclipse-feature"));

    @Component(role = TychoProject.class)
    private Map<String, TychoProject> projectTypes;

    /**
     * Additional dependencies to package
     */
    @Parameter
    private Dependency[] additionalDependencies;

    @Component
    private ArtifactResolver resolver;

    @Parameter(property = "localRepository")
    private ArtifactRepository localRepository;

    @Parameter(property = "project.remoteArtifactRepositories")
    private List<ArtifactRepository> remoteRepositories;

    @Parameter(property = "version")
    private String version;

    protected ArtifactWalker lookupDependencyWalker() {
        final String packaging = this.project.getPackaging();

        if (!this.tychoWalkerProjects.contains(packaging)) {
            return new ProjectWalker(this.project);
        }

        final TychoProject facet = this.projectTypes.get(packaging);
        if (facet == null) {
            throw new IllegalStateException(format("Unknown packaging '%s'", packaging));
        }

        return new TychoWalker(facet.getDependencyWalker(this.project), getLog());
    }

    public void setVersion(final String version) {
        this.version = Version.parseVersion(version).toString();
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

            final String dpName = String.format("%s_%s.dp", this.project.getArtifactId(), makeVersion(false));
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

    private void fillFromDependencies(final Manifest dpmf, final Map<String, File> files)
            throws IOException, MojoExecutionException {

        // from dependency walker

        final ArtifactWalker dw = lookupDependencyWalker();
        dw.walk(entry -> processArtifact(dpmf, files, entry.getLocation().toFile()));

        // additional dependencies

        if (this.additionalDependencies != null) {

            try {
                final Collection<ArtifactRequest> requests = new ArrayList<>(this.additionalDependencies.length);

                for (final Dependency dep : this.additionalDependencies) {
                    final DefaultArtifact art = new DefaultArtifact(dep.getGroupId(), dep.getArtifactId(),
                            dep.getClassifier(), dep.getType(), dep.getVersion());
                    final org.eclipse.aether.graph.Dependency adep = new org.eclipse.aether.graph.Dependency(art,
                            JavaScopes.RUNTIME);
                    requests.add(new ArtifactRequest(new DefaultDependencyNode(adep)));
                }

                final List<ArtifactResult> result = this.resolver.resolveArtifacts(this.repositorySession, requests);

                for (final ArtifactResult ares : result) {
                    getLog().debug("Additional dependency: " + ares);
                    processArtifact(dpmf, files, ares.getArtifact().getFile());
                }
            } catch (final ArtifactResolutionException e) {
                throw new MojoExecutionException("Failed to resolve additional dependencies", e);
            }
        }
    }

    private void processArtifact(final Manifest dpmf, final Map<String, File> files, final File location)
            throws IOException {

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

    private String makeVersion(final boolean osgiVersion) {
        if (this.version != null) {
            return this.version.toString();
        }

        if (this.useQualifiedFilename) {
            return makeQualifiedVersion().toString();
        }

        if (osgiVersion) {
            return makeQualifiedVersion().toString();
        } else {
            return this.project.getVersion();
        }
    }

    private Version makeQualifiedVersion() {
        try {
            final ReactorProject rp = (ReactorProject) this.project.getContextValue(ReactorProject.CTX_REACTOR_PROJECT);
            return new Version(rp.getExpandedVersion());
        } catch (final Exception e) {
            getLog().info("Failed to get qualified tycho version", e);
        }

        final String version = this.project.getVersion();
        if (version.endsWith("-SNAPSHOT")) {
            version.replaceAll("-SNAPSHOT$", TIMESTAMP_FORMAT.format("." + this.session.getStartTime()));
        }

        return new Version(version);
    }
}

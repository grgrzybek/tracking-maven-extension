/*
 * Copyright 2022 OPS4J.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ops4j.tools.maven.tracker;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import org.apache.maven.model.InputLocation;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.FileModelSource;
import org.codehaus.plexus.component.annotations.Component;
import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositoryListener;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectStepData;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.DependencyRequest;

@Component(role = RepositoryListener.class)
public class TrackingRepositoryListener extends AbstractRepositoryListener {

    @Override
    public void artifactResolved(RepositoryEvent event) {
        write(event);
    }

    @Override
    public void metadataResolved(RepositoryEvent event) {
        write(event);
    }

    private void write(RepositoryEvent event) {
        if (event.getArtifact() == null) {
            // nothing we can track
            return;
        }
        if (event.getRepository() != null && "workspace".equalsIgnoreCase(event.getRepository().getId())) {
            // artifact resolved through the reactor/IDE - no need to track it
            return;
        }

        File dir;
        boolean missing = false;

        if (event.getFile() == null) {
            // missing artifact - let's track the path anyway
            missing = true;
            dir = event.getSession().getLocalRepository().getBasedir();
            dir = new File(dir, event.getSession().getLocalRepositoryManager().getPathForLocalArtifact(event.getArtifact()));
            dir = dir.getParentFile();
        } else {
            dir = event.getFile().getParentFile();
        }

        // https://github.com/apache/maven-resolver/pull/182
        // the most important data we're looking for is org.eclipse.aether.collection.CollectStepData
        // which contains the path to the resolved artifact
        // but we can also add some more information from other kinds of org.eclipse.aether.RequestTrace.data

        RequestTrace trace = event.getTrace();

        ArtifactDescriptorRequest adr = null;
        CollectRequest cr = null;
        CollectStepData csd = null;
        ArtifactRequest ar = null;
        Plugin plugin = null;
        DependencyRequest dr = null;
//        DefaultDependencyResolutionRequest ddrr = null;
        DefaultModelBuildingRequest dmbr = null;

        while (trace != null) {
            Object data = trace.getData();
            if (data instanceof ArtifactDescriptorRequest) {
                adr = (ArtifactDescriptorRequest) data;
            } else if (data instanceof CollectStepData) {
                csd = (CollectStepData) data;
//            } else if (data instanceof DefaultDependencyResolutionRequest) {
//                ddrr = (DefaultDependencyResolutionRequest) data;
            } else if (data instanceof DependencyRequest) {
                dr = (DependencyRequest) data;
            } else if (data instanceof ArtifactRequest) {
                ar = (ArtifactRequest) data;
            } else if (data instanceof Plugin) {
                plugin = (Plugin) data;
            } else if (data instanceof DefaultModelBuildingRequest) {
                dmbr = (DefaultModelBuildingRequest) data;
            }
            trace = trace.getParent();
        }

        try {
            Path trackingDir = dir.toPath().resolve(".tracking");
            Files.createDirectories(trackingDir);

            String baseName;
            String ext = missing ? ".miss" : ".dep";
            Path trackingFile = null;

            StringBuilder sb = new StringBuilder();

            if (csd == null) {
                // no recorder path to the artifact resolved
                if (plugin != null) {
                    ext = ".plugin";
                    baseName = plugin.getGroupId() + "_" + plugin.getArtifactId() + "_" + plugin.getVersion();
                    trackingFile = trackingDir.resolve(baseName + ext);
                    if (Files.exists(trackingFile)) {
                        return;
                    }

                    StringBuilder indent = new StringBuilder();

                    if (ar != null && ar.getArtifact() != null) {
                        sb.append(indent.toString()).append(ar.getArtifact()).append("\n");
                        indent.append("  ");
                    }

                    sb.append(indent.toString())
                            .append(plugin.getGroupId())
                            .append(":")
                            .append(plugin.getArtifactId())
                            .append(":")
                            .append(plugin.getVersion())
                            .append("\n");
                    indent.append("  ");

                    InputLocation location = plugin.getLocation("");
                    if (location != null && location.getSource() != null) {
                        sb.append(indent.toString()).append(location.getSource().getModelId()).append(" (implicit)\n");
                    }
                } else if (dr != null) {
                    baseName = dr.getRoot().toString().replace(":", "_");
                    trackingFile = trackingDir.resolve(baseName + ext);
                    if (Files.exists(trackingFile)) {
                        return;
                    }

                    StringBuilder indent = new StringBuilder();

                    if (ar != null && ar.getArtifact() != null) {
                        sb.append(indent.toString()).append(ar.getArtifact()).append("\n");
                        indent.append("  ");
                    }

                    sb.append(indent.toString()).append(dr.getRoot()).append("\n");
                } else if (dmbr != null) {
                    // null pomfile for org.apache.maven.project.artifact.MavenMetadataSource.retrieveRelocatedProject()
                    File file = dmbr.getPomFile();
                    if (file == null && dmbr.getModelSource() instanceof FileModelSource) {
                        file = ((FileModelSource) dmbr.getModelSource()).getFile();
                    }
                    if (file == null) {
                        return;
                    }
                    baseName = file.getAbsolutePath().replace("/", "_").replace("\\", "_").replace(":", "_");
                    while (baseName.startsWith("_")) {
                        baseName = baseName.substring(1);
                    }
                    trackingFile = trackingDir.resolve(baseName + ext);
                    if (Files.exists(trackingFile)) {
                        return;
                    }

                    StringBuilder indent = new StringBuilder();

                    if (ar != null && ar.getArtifact() != null) {
                        sb.append(indent.toString()).append(ar.getArtifact()).append("\n");
                        indent.append("  ");
                    }
                    sb.append(indent.toString()).append(file.getAbsolutePath()).append("\n");
                }
            } else {
                baseName = csd.getPath().get(0).getArtifact().toString().replace(":", "_");
                trackingFile = trackingDir.resolve(baseName + ext);
                if (Files.exists(trackingFile)) {
                    return;
                }

                StringBuilder indent = new StringBuilder();

                if (ar != null && ar.getArtifact() != null) {
                    sb.append(indent.toString()).append(ar.getArtifact()).append("\n");
                    indent.append("  ");
                }

                sb.append(indent.toString()).append(csd.getNode())
                        .append(" (")
                        .append(csd.getContext())
                        .append(")\n");

                // we have a path to dependency
                ListIterator<DependencyNode> iter = csd.getPath().listIterator(csd.getPath().size());
                while (iter.hasPrevious()) {
                    DependencyNode curr = iter.previous();
                    indent.append("  ");
                    sb.append(indent.toString()).append(curr)
                            .append(" (")
                            .append(csd.getContext())
                            .append(")\n");
                }
            }

            if (trackingFile != null) {
                if (!missing) {
                    if (event.getRepository() != null) {
                        sb.append("\nRepository: ").append(event.getRepository()).append("\n");
                    }
                } else {
                    List<RemoteRepository> repositories = new ArrayList<>();
                    if (ar != null && ar.getRepositories() != null) {
                        repositories.addAll(ar.getRepositories());
                    } else if (adr != null && adr.getRepositories() != null) {
                        repositories.addAll(adr.getRepositories());
                    }
                    if (!repositories.isEmpty()) {
                        sb.append("\nConfigured repositories:\n");
                        for (RemoteRepository r : repositories) {
                            sb.append(" * ").append(r.getId()).append(" : ").append(r.getUrl()).append("\n");
                        }
                    } else {
                        sb.append("\nConfigured repositories:\n");
                    }
                }
                Files.write(trackingFile, sb.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e.getMessage(), e);
        }
    }

}

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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.apache.maven.model.InputLocation;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.codehaus.plexus.component.annotations.Component;
import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositoryListener;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.DependencyRequest;

@Component(role = RepositoryListener.class)
public class TrackingRepositoryListener extends AbstractRepositoryListener {

    static Deque<DependencyNode> stack = new ConcurrentLinkedDeque<>();

    @Override
    public void artifactDownloaded(RepositoryEvent event) {
        write(event);
        super.artifactDownloaded(event);
    }

    @Override
    public void artifactDownloading(RepositoryEvent event) {
        super.artifactDownloading(event);
    }

    @Override
    public void metadataDownloaded(RepositoryEvent event) {
        write(event);
        super.metadataDownloaded(event);
    }

    @Override
    public void metadataDownloading(RepositoryEvent event) {
        super.metadataDownloading(event);
    }

    private static final String[] INDENTS = new String[] {
            "", "  ", "    ", "      ", "        ", "          ", "            "
    };

    private void write(RepositoryEvent event) {
        if (event.getFile() == null) {
            return;
        }
        File dir = event.getFile().getParentFile();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(dir, "_dependency-tracker.txt"), true))) {
            RequestTrace trace = event.getTrace();
            writer.write("~~~\n");
            int indent = 0;
            while (trace != null) {
                Object data = trace.getData();
                if (data instanceof ArtifactDescriptorRequest) {
                    ArtifactDescriptorRequest adr = (ArtifactDescriptorRequest) data;
                    Artifact a = adr.getArtifact();
                    String scope = "?";
                    CollectRequest cr = null;
                    RequestTrace _trace = trace;
                    while (_trace != null) {
                        if (_trace.getData() instanceof CollectRequest) {
                            for (Dependency d : ((CollectRequest) _trace.getData()).getDependencies()) {
                                if (d != null && d.getArtifact() != null && d.getArtifact() == a) {
                                    scope = d.getScope();
                                    if (d.isOptional()) {
                                        scope += "/optional";
                                    }
                                    break;
                                }
                            }
                            break;
                        }
                        _trace = _trace.getParent();
                    }
                    writer.write(String.format("%sReading descriptor for artifact %s:%s:%s%s:%s (context: %s) (scope: %s) (repository: %s)\n",
                            INDENTS[indent], a.getGroupId(), a.getArtifactId(), a.getExtension(),
                            a.getClassifier() != null ? ":" + a.getClassifier() : "",
                            a.getVersion(), adr.getRequestContext(), scope, event.getRepository() == null ? "?" : event.getRepository().toString()));
                    indent++;
                } else if (data instanceof ArtifactRequest) {
                    ArtifactRequest ar = (ArtifactRequest) data;
                    Artifact a = ar.getArtifact();
                    writer.write(String.format("%sDownloaded artifact %s:%s:%s%s:%s (repository: %s)\n",
                            INDENTS[indent], a.getGroupId(), a.getArtifactId(), a.getExtension(),
                            a.getClassifier() != null ? ":" + a.getClassifier() : "",
                            a.getVersion(), event.getRepository() == null ? "?" : event.getRepository().toString()));
                    int id2 = 1;
                    for (DependencyNode dn : stack) {
                        StringBuilder indent2 = new StringBuilder();
                        for (int i = 0; i < indent + id2; i++) {
                            indent2.append("  ");
                        }
                        id2++;
                        indent2.append(" -> ");
                        writer.write(String.format("%s%s (context: %s)\n", indent2.toString(), dn.toString(), dn.getRequestContext()));
                    }
                    trackDependencies(stack, dir, event.getArtifact());

                    indent++;
                } else if (data instanceof CollectRequest) {
                    CollectRequest cr = (CollectRequest) data;
                    if (cr.getRoot() != null) {
                        writer.write(String.format("%sTransitive dependencies collection for %s\n",
                                INDENTS[indent], cr.getRoot()));
                    }
                    if (cr.getRootArtifact() != null) {
                        writer.write(String.format("%sTransitive dependencies collection for %s\n",
                                INDENTS[indent], cr.getRootArtifact()));
                    }
                    indent++;
                } else if (data instanceof DefaultModelBuildingRequest) {
                    DefaultModelBuildingRequest mbr = (DefaultModelBuildingRequest) data;
                    writer.write(String.format("%sModel building for %s\n",
                            INDENTS[indent], mbr.getModelSource().getLocation()));
                    indent++;
                } else if (data instanceof DefaultDependencyResolutionRequest) {
                    DefaultDependencyResolutionRequest drr = (DefaultDependencyResolutionRequest) data;
                } else if (data instanceof DependencyRequest) {
                    DependencyRequest dr = (DependencyRequest) data;
                } else if (data instanceof Plugin) {
                    Plugin plugin = (Plugin) data;
                    InputLocation location = plugin.getLocation("");
                    String modelId = "?";
                    if (location != null) {
                        modelId = location.getSource() == null ? "?" : location.getSource().getModelId();
                    }
                    writer.write(String.format("%sResolution of plugin %s:%s:%s (%s)\n",
                            INDENTS[indent], plugin.getGroupId(), plugin.getArtifactId(), plugin.getVersion(), modelId));
                    indent++;
                }
                trace = trace.getParent();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void trackDependencies(Deque<DependencyNode> stack, File dir, Artifact artifact) {
        if (artifact == null) {
            return;
        }
        File dir2 = new File(dir, ".tracking");
        if (dir2.mkdirs() || dir2.isDirectory()) {
            DependencyNode dep = TrackingRepositoryListener.stack.peekLast();
            if (dep != null) {
                String directRequirer = dep.getArtifact().toString().replace(":", "_") + ".dep";
                File tracker = new File(dir2, directRequirer);
                if (!tracker.isFile()) {
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(tracker))) {
                        writer.write(String.format("%s\n", artifact.toString()));
                        int indent = 0;
                        for (DependencyNode dn : TrackingRepositoryListener.stack) {
                            StringBuilder indent2 = new StringBuilder();
                            for (int i = 0; i < indent; i++) {
                                indent2.append("  ");
                            }
                            indent++;
                            indent2.append(" -> ");
                            writer.write(String.format("%s%s (context: %s)\n", indent2.toString(), dn.toString(), dn.getRequestContext()));
                        }
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }

}

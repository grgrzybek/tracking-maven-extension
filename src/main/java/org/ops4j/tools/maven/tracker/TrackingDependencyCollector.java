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

import java.util.List;
import javax.inject.Inject;

import org.codehaus.plexus.component.annotations.Component;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.DependencyManager;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.collection.DependencyTraverser;
import org.eclipse.aether.collection.VersionFilter;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.impl.ArtifactDescriptorReader;
import org.eclipse.aether.impl.DependencyCollector;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.impl.VersionRangeResolver;
import org.eclipse.aether.internal.impl.collect.DefaultDependencyCollector;
import org.eclipse.aether.repository.RemoteRepository;

@Component(role = DependencyCollector.class)
public class TrackingDependencyCollector extends DefaultDependencyCollector {

    @Inject
    TrackingDependencyCollector(RemoteRepositoryManager remoteRepositoryManager,
            ArtifactDescriptorReader artifactDescriptorReader,
            VersionRangeResolver versionRangeResolver) {
        this.setRemoteRepositoryManager(remoteRepositoryManager);
        this.setArtifactDescriptorReader(artifactDescriptorReader);
        this.setVersionRangeResolver(versionRangeResolver);
    }

    @Override
    protected void processDependency(DefaultDependencyCollector.Args args, DefaultDependencyCollector.Results results, List<RemoteRepository> repositories, DependencySelector depSelector, DependencyManager depManager, DependencyTraverser depTraverser, VersionFilter verFilter, Dependency dependency, List<Artifact> relocations, boolean disableVersionManagement) {
        TrackingRepositoryListener.stack.push(args.nodes.top());
        super.processDependency(args, results, repositories, depSelector, depManager, depTraverser, verFilter, dependency, relocations, disableVersionManagement);
        TrackingRepositoryListener.stack.pop();
    }

}

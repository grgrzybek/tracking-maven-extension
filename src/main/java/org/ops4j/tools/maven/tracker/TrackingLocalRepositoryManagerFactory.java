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

import javax.inject.Named;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.internal.impl.EnhancedLocalRepositoryManagerFactory;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.LocalArtifactRegistration;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalArtifactResult;
import org.eclipse.aether.repository.LocalMetadataRegistration;
import org.eclipse.aether.repository.LocalMetadataRequest;
import org.eclipse.aether.repository.LocalMetadataResult;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
import org.eclipse.aether.repository.RemoteRepository;

@Named("enhanced")
public class TrackingLocalRepositoryManagerFactory extends EnhancedLocalRepositoryManagerFactory {

    @Override
    public LocalRepositoryManager newInstance(RepositorySystemSession session, LocalRepository repository) throws NoLocalRepositoryManagerException {
        return new TrackingLocalRepositoryManager(super.newInstance(session, repository));
    }

    public static class TrackingLocalRepositoryManager implements LocalRepositoryManager {

        private final LocalRepositoryManager delegate;

        public TrackingLocalRepositoryManager(LocalRepositoryManager delegate) {
            this.delegate = delegate;
        }

        @Override
        public LocalRepository getRepository() {
            return delegate.getRepository();
        }

        @Override
        public String getPathForLocalArtifact(Artifact artifact) {
            return delegate.getPathForLocalArtifact(artifact);
        }

        @Override
        public String getPathForRemoteArtifact(Artifact artifact, RemoteRepository repository, String context) {
            return delegate.getPathForRemoteArtifact(artifact, repository, context);
        }

        @Override
        public String getPathForLocalMetadata(Metadata metadata) {
            return delegate.getPathForLocalMetadata(metadata);
        }

        @Override
        public String getPathForRemoteMetadata(Metadata metadata, RemoteRepository repository, String context) {
            return delegate.getPathForRemoteMetadata(metadata, repository, context);
        }

        @Override
        public LocalArtifactResult find(RepositorySystemSession session, LocalArtifactRequest request) {
            LocalArtifactResult result = delegate.find(session, request);
            if (result != null && result.getFile() != null) {
                // track the dependency chain
                TrackingRepositoryListener.trackDependencies(TrackingRepositoryListener.stack,
                        result.getFile().getParentFile(), result.getRequest().getArtifact());
            }
            return result;
        }

        @Override
        public void add(RepositorySystemSession session, LocalArtifactRegistration request) {
            delegate.add(session, request);
        }

        @Override
        public LocalMetadataResult find(RepositorySystemSession session, LocalMetadataRequest request) {
            return delegate.find(session, request);
        }

        @Override
        public void add(RepositorySystemSession session, LocalMetadataRegistration request) {
            delegate.add(session, request);
        }
    }

}

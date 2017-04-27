/*
 *  Copyright 2013-2016 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License")
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.springframework.cloud.contract.stubrunner.provider.moco

import groovy.transform.CompileStatic
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.springframework.cloud.contract.stubrunner.StubConfiguration
import org.springframework.cloud.contract.stubrunner.StubDownloader
import org.springframework.cloud.contract.stubrunner.StubDownloaderBuilder
import org.springframework.cloud.contract.stubrunner.StubRunnerOptions
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.Resource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver

import java.lang.invoke.MethodHandles
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.MatchResult
import java.util.regex.Matcher
import java.util.regex.Pattern
/**
 * Stub downloader that picks stubs and contracts from the provided resource.
 * If no {@link org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties#repositoryRoot}
 * is provided then by default classpath is searched according to what has been passed in
 * {@link org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties#ids}. The
 * pattern to search for stubs looks like this
 * <ul>
 *     <li>{@code META-INF/group.id/artifactid/ ** /*.* }</li>
 *     <li>{@code contracts/group.id/artifactid/ ** /*.* }</li>
 *     <li>{@code mappings/group.id/artifactid/ ** /*.* }</li>
 * </ul>
 *
 * examples
 *
 * <ul>
 *     <li>{@code META-INF/com.example/fooservice/1.0.0/ **}</li>
 *     <li>{@code contracts/com.example/artifactid/ ** /*.* }</li>
 *     <li>{@code mappings/com.example/artifactid/ ** /*.* }</li>
 * </ul>
 *
 * @author Marcin Grzejszczak
 * @since 1.1.1
 */
@CompileStatic
class ResourceStubProvider implements StubDownloaderBuilder {

	private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass())

	private static final int TEMP_DIR_ATTEMPTS = 10000
	PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(
			new DefaultResourceLoader())

	@Override
	StubDownloader build(StubRunnerOptions stubRunnerOptions) {
		return new StubDownloader() {
			@Override
			Map.Entry<StubConfiguration, File> downloadAndUnpackStubJar(
					StubConfiguration config) {
				List<RepoRoot> repoRoot = repoRoot(stubRunnerOptions, config)
				List<Resource> resources = resolveResources(repoRoot.fullPath)
				if (log.isDebugEnabled()) {
					log.debug("For paths " + repoRoot.fullPath + " found following resources " + resources)
				}
				final File tmp = createTempDir()
				tmp.deleteOnExit()
				Pattern groupAndArtifactPattern = Pattern.compile("^(.*)(" + config.groupId + '.' + config.artifactId + ')(.*)$')
				String version = config.version
				resources.each { Resource resource ->
					String relativePath = relativePathPicker(resource, groupAndArtifactPattern)
					int lastIndexOf = relativePath.lastIndexOf(File.separator)
					String relativePathWithoutFile = lastIndexOf > -1 ? relativePath.substring(0, lastIndexOf) : relativePath
					Path directory = Files.createDirectories(new File(tmp, relativePathWithoutFile).toPath())
					File newFile = new File(directory.toFile(), resource.filename)
					if (!newFile.exists()) {
						Files.copy(resource.getInputStream(), newFile.toPath())
					}
					if (log.isDebugEnabled()) {
						log.debug("Stored file [" + newFile + "]")
					}
				}
				log.info("Unpacked files for [" + config.groupId + ":" + config.artifactId +
				":" + version + "] to folder [" + tmp + "]")
				return new AbstractMap.SimpleEntry(
						new StubConfiguration(config.groupId, config.artifactId, version,
								config.classifier), tmp)
			}

			protected static String relativePathPicker(Resource resource, Pattern groupAndArtifactPattern) {
				String uri = resource.URI.toString()
				Matcher groupAndArtifactMatcher = groupAndArtifactPattern.matcher(uri)
				if (groupAndArtifactMatcher.matches()) {
					MatchResult groupAndArtifactResult = groupAndArtifactMatcher.toMatchResult()
					return groupAndArtifactResult.group(2) + File.separator + groupAndArtifactResult.group(3)
				} else {
					throw new IllegalArgumentException("Illegal uri [${uri}]")
				}
			}

		}
	}

	List<Resource> resolveResources(List<String> paths) {
		return paths.inject([] as List<Resource>) { List<Resource> acc, String path ->
			acc.addAll(this.resolver.getResources(path))
			return acc
		} as List<Resource>
	}

	private List<RepoRoot> repoRoot(StubRunnerOptions stubRunnerOptions, StubConfiguration configuration) {
		switch (stubRunnerOptions.stubRepositoryRoot) {
			case { !it }:
				return ['META-INF', 'contracts', 'mappings'].collect {
					return new RepoRoot("classpath*:/${it}/**/${configuration.groupId}/${configuration.artifactId}", "/**/*.*")
				}
			default:
				return [new RepoRoot(stubRunnerOptions.stubRepositoryRoot)]
		}
	}

	// Taken from Guava
	private File createTempDir() {
		File baseDir = new File(System.getProperty("java.io.tmpdir"))
		String baseName = System.currentTimeMillis() + "-"
		for (int counter = 0; counter < TEMP_DIR_ATTEMPTS; counter++) {
			File tempDir = new File(baseDir, baseName + counter)
			if (tempDir.mkdir()) {
				return tempDir
			}
		}
		throw new IllegalStateException(
				"Failed to create directory within " + TEMP_DIR_ATTEMPTS + " attempts (tried " + baseName + "0 to " + baseName + (
						TEMP_DIR_ATTEMPTS - 1) + ")")
	}

	private static class RepoRoot {
		final String repoRoot
		final String fullPath

		RepoRoot(String repoRoot, String suffix = "") {
			this.repoRoot = repoRoot
			this.fullPath = repoRoot + suffix
		}
	}
}
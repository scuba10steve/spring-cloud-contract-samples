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
 * is provided then by default classpath is searched according to wha thas been passed in
 * {@link org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties#ids}. The
 * pattern to search for stubs looks like this {@code /META-INF/group/id/artifactid/version/** }
 * example {@code /META-INF/com/example/fooservice/1.0.0/**}
 *
 * @author Marcin Grzejszczak
 * @since 1.1.1
 */
class ResourceStubProvider implements StubDownloaderBuilder {

	private static final Log log = LogFactory.getLog(MethodHandles.lookup().lookupClass())

	private static final int TEMP_DIR_ATTEMPTS = 10000
	PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(
			new DefaultResourceLoader())
	private static final Pattern VERSION = Pattern.compile("^(.*/META-INF/(.*)\$)")

	@Override
	StubDownloader build(StubRunnerOptions stubRunnerOptions) {
		return new StubDownloader() {
			@Override
			Map.Entry<StubConfiguration, File> downloadAndUnpackStubJar(
					StubConfiguration stubConfiguration) {
				RepoRoot repoRoot = repoRoot(stubRunnerOptions, stubConfiguration)
				Resource[] resources = resolver.getResources(repoRoot.fullPath)
				final File tmp = createTempDir()
				tmp.deleteOnExit()
				String version = ""
				resources.each { Resource resource ->
					Matcher matcher = VERSION.matcher(resource.URI.toString())
					boolean matches = matcher.matches()
					MatchResult matchResult = matcher.toMatchResult()
					String reminder = matches ? matchResult.group(2) : ""
					String withoutGroupAndArtifact = reminder - separatedArtifact(stubConfiguration) - File.separator
					version = version ?: matches ? withoutGroupAndArtifact.substring(0, withoutGroupAndArtifact.indexOf(File.separator)) : ""
					String relativePath = matches ? withoutGroupAndArtifact - version : ""
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
				log.info("Unpacked files for [" + stubConfiguration.groupId + ":" + stubConfiguration.artifactId +
				":" + version + "] to folder [" + tmp + "]")
				return new AbstractMap.SimpleEntry(
						new StubConfiguration(stubConfiguration.groupId, stubConfiguration.artifactId, version,
								stubConfiguration.classifier), tmp)
			}
		}
	}

	private RepoRoot repoRoot(StubRunnerOptions stubRunnerOptions, StubConfiguration stubConfiguration) {
		switch (stubRunnerOptions.stubRepositoryRoot) {
			case { !it }:
				return new RepoRoot("classpath:/META-INF/" + separatedArtifact(stubConfiguration), "/**/*.*")
			default:
				return new RepoRoot(stubRunnerOptions.stubRepositoryRoot)
		}
	}

	private String separatedArtifact(StubConfiguration configuration) {
		return configuration.getGroupId().replace(".", File.separator) +
				File.separator + configuration.getArtifactId()
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
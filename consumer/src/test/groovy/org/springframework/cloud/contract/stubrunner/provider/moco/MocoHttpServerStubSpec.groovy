/*
 *  Copyright 2013-2016 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
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

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootContextLoader
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.stubrunner.StubFinder
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner
import org.springframework.cloud.stream.annotation.EnableBinding
import org.springframework.cloud.stream.annotation.StreamListener
import org.springframework.cloud.stream.messaging.Sink
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification
/**
 * @author Marcin Grzejszczak
 */
@ContextConfiguration(classes = Config, loader = SpringBootContextLoader)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@AutoConfigureStubRunner( ids = ["com.example:fraudDetectionServerMoco"])
@DirtiesContext
class MocoHttpServerStubSpec extends Specification {

	@Autowired StubFinder stubFinder
	@Autowired MyListener myListener

	def 'should successfully receive a response from a stub'() {
		given:
			String url = stubFinder.findStubUrl('fraudDetectionServerMoco').toString()
		expect:
			"${url.toString()}/name".toURL().text == 'fraudDetectionServerMoco'
			"${url.toString()}/bye".toURL().text == 'bye'
		and:
			stubFinder.trigger("send_order")
		and:
			myListener.model?.description == "This is the order description"
	}

	@Configuration
	@EnableAutoConfiguration
	@EnableBinding(Sink.class)
	static class Config {

		@Bean
		MyListener myListener() {
			return new MyListener()
		}
	}

	@Component
	static class MyListener {

		Model model

		@StreamListener(Sink.INPUT)
		void listen(Model data) {
			this.model = data
		}

	}

	static class Model {
		String uuid
		String description
	}
}

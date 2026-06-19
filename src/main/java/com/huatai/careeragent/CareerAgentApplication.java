package com.huatai.careeragent;

import com.huatai.careeragent.file.FileStorageProperties;
import com.huatai.careeragent.knowledge.chunk.ChunkingProperties;
import com.huatai.careeragent.knowledge.embedding.EmbeddingProperties;
import com.huatai.careeragent.knowledge.retrieval.RetrievalProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
		FileStorageProperties.class,
		ChunkingProperties.class,
		EmbeddingProperties.class,
		RetrievalProperties.class
})
public class CareerAgentApplication {

	public static void main(String[] args) {
		SpringApplication.run(CareerAgentApplication.class, args);
	}

}

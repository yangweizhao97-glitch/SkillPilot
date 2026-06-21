package com.huatai.careeragent;

import com.huatai.careeragent.file.FileStorageProperties;
import com.huatai.careeragent.knowledge.chunk.ChunkingProperties;
import com.huatai.careeragent.knowledge.embedding.EmbeddingProperties;
import com.huatai.careeragent.knowledge.retrieval.RetrievalProperties;
import com.huatai.careeragent.llm.ModelConfig;
import com.huatai.careeragent.agent.core.AgentProperties;
import com.huatai.careeragent.mcp.McpProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
		FileStorageProperties.class,
		ChunkingProperties.class,
		EmbeddingProperties.class,
		RetrievalProperties.class,
		ModelConfig.class,
		AgentProperties.class,
		McpProperties.class
})
public class CareerAgentApplication {

	public static void main(String[] args) {
		SpringApplication.run(CareerAgentApplication.class, args);
	}

}

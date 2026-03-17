package br.com.banco.processamento_encargos.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class KafkaConsumerConfigTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ConsumerFactory<String, String> consumerFactory;

    private KafkaConsumerConfig config;

    @BeforeEach
    void setUp() {
        config = new KafkaConsumerConfig();
    }

    @Test
    @DisplayName("kafkaErrorHandler deve criar DefaultErrorHandler não nulo")
    void deveCriarKafkaErrorHandler() {
        DefaultErrorHandler handler = config.kafkaErrorHandler(kafkaTemplate);

        assertNotNull(handler);
    }

    @Test
    @DisplayName("kafkaListenerContainerFactory deve criar factory com error handler configurado")
    void deveCriarKafkaListenerContainerFactory() {
        DefaultErrorHandler handler = config.kafkaErrorHandler(kafkaTemplate);

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                config.kafkaListenerContainerFactory(consumerFactory, handler);

        assertNotNull(factory);
    }
}
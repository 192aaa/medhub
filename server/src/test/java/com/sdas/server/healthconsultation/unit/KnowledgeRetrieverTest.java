package com.sdas.server.healthconsultation.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sdas.server.config.HealthProperties;
import com.sdas.server.healthconsultation.repository.KnowledgeRepository;
import com.sdas.server.healthconsultation.support.KnowledgeHit;
import com.sdas.server.healthconsultation.support.KnowledgeRetriever;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 知识检索候选词与空命中。 */
class KnowledgeRetrieverTest {

    private final KnowledgeRepository repository = mock(KnowledgeRepository.class);

    private final HealthProperties properties =
            new HealthProperties("deepseek-v4-flash", 8000, 3000, 1024, 6000, 1500, 12, 2000, 200, 5);

    private final KnowledgeRetriever retriever = new KnowledgeRetriever(repository, properties);

    @Test
    @DisplayName("中文按 2/3-gram 生成候选词并去重")
    void buildsNgrams() {
        when(repository.searchByKeywords(anyList(), anyInt())).thenReturn(List.of(
                new KnowledgeHit("咳嗽：保持湿度", 1L, "2026.11", List.of("咳嗽"))));

        List<KnowledgeHit> hits = retriever.retrieve("我这两天咳嗽有痰", 5);

        assertThat(hits).hasSize(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).searchByKeywords(captor.capture(), org.mockito.ArgumentMatchers.eq(5));
        assertThat(captor.getValue()).contains("咳嗽");
        assertThat(captor.getValue().size()).isLessThanOrEqualTo(40);
    }

    @Test
    @DisplayName("空输入直接返回空列表且不查询")
    void emptyInputSkipsQuery() {
        assertThat(retriever.retrieve("   ", 5)).isEmpty();
        verify(repository, org.mockito.Mockito.never()).searchByKeywords(anyList(), anyInt());
    }
}

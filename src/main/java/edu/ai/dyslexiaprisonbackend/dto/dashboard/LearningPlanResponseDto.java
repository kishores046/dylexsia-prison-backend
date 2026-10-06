package edu.ai.dyslexiaprisonbackend.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningPlanResponseDto {
    private String classification;
    private String language;
    private List<ModuleDto> modules;
}

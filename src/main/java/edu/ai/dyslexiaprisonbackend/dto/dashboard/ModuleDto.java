package edu.ai.dyslexiaprisonbackend.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModuleDto {
    private Long id;
    private String title;
    private String type;
    private boolean completed;
    private String audioUrl;
}

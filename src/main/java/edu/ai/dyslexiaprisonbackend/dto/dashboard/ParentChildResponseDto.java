package edu.ai.dyslexiaprisonbackend.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParentChildResponseDto {
    private Long studentId;
    private String name;
    private String latestClassification;
    private Double latestScore;
}

package edu.ai.dyslexiaprisonbackend.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BreakdownDto {
    private Double ruleScore;
    private Double rfScore;
}

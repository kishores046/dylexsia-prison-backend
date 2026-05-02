package edu.ai.dyslexiaprisonbackend.model.user;

import jakarta.persistence.Embeddable;
import lombok.*;


@Embeddable
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Address {

    private String street;
    private String area;
    private String city;
    private String state;
    private String postalCode;
    private String country;
}
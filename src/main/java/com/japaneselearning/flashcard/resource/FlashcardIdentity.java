package com.japaneselearning.flashcard.resource;

import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.NotAuthorizedException;
import org.eclipse.microprofile.jwt.JsonWebToken;

@RequestScoped
public class FlashcardIdentity {
    private final JsonWebToken jwt;

    public FlashcardIdentity(JsonWebToken jwt) {
        this.jwt = jwt;
    }

    public String subject() {
        String subject = jwt.getSubject();

        if (subject == null
                || subject.isBlank()
                || subject.codePointCount(0, subject.length()) > 255
        ) {
            throw new NotAuthorizedException("Bearer");
        }

        return subject;
    }
}

package stroom.stats.service.resources;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.query.api.v2.DocRef;

public class AuthorisationRequest {
    @JsonProperty
    private DocRef docRef;
    @JsonProperty
    private String permissions;

    public AuthorisationRequest(DocRef docRef, String permissions){
        this.docRef = docRef;
        this.permissions = permissions;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public String getPermissions() {
        return permissions;
    }
}

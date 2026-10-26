package com.example.demo.common.identity;
/**
* Boundary contract for the future authentication adapter.
* No production implementation is supplied by the User/Address feature.
*/
@FunctionalInterface
public interface CurrentUserIdProvider {
    /** @return the trusted immutable user ID; never a client ownership claim
    * @throws IdentityUnavailableException when no authenticated identity is available
    */
    Long getCurrentUserId();
}

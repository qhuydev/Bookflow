package com.bookflow.authentication.repository;

import com.bookflow.authentication.domain.NewUser;

public interface UserRegistrationRepository {

    void insert(NewUser user);
}

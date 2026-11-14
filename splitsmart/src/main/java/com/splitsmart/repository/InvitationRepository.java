package com.splitsmart.repository;

import com.splitsmart.model.Invitation;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationRepository extends JpaRepository<Invitation, String> {
    List<Invitation> findByTargetUsernameAndStatus(String target, String status);

    boolean existsByGroupIdAndTargetUsernameAndStatus(String group, String target, String status);
}

package com.splitsmart.repository;

import com.splitsmart.model.Membership;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRepository extends JpaRepository<Membership, String> {
    Optional<Membership> findByGroupIdAndUsername(String groupId, String username);

    List<Membership> findByUsername(String username);

    List<Membership> findByGroupId(String groupId);
}

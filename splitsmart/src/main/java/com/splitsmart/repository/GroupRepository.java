package com.splitsmart.repository;

import com.splitsmart.model.GroupEntity;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface GroupRepository extends JpaRepository<GroupEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GroupEntity g where g.id = :id")
    Optional<GroupEntity> lockById(String id);

    List<GroupEntity> findByOwnerUsernameIsNull();
}

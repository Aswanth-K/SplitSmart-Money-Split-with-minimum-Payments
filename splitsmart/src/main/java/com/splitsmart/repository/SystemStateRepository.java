package com.splitsmart.repository;

import com.splitsmart.model.SystemState;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface SystemStateRepository extends JpaRepository<SystemState, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SystemState s where s.id = 'legacy-import-v1'")
    Optional<SystemState> registrationLock();
}

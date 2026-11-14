package com.splitsmart.repository;

import com.splitsmart.model.Account;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface AccountRepository extends JpaRepository<Account, String> {
    @Query(
            "select a from Account a where not exists (select m from Membership m where m.groupId=:group and m.username=a.username) and (:query='' or locate(:query,lower(a.username))>0 or locate(:query,lower(a.displayName))>0) order by a.username")
    List<Account> searchAvailable(String group, String query, Pageable limit);
}

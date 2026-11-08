package com.gi3.matchingcv.repository;
import com.gi3.matchingcv.model.AccountToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface AccountTokenRepository extends JpaRepository<AccountToken,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) Optional<AccountToken> findByHash(String hash);
    void deleteByUserIdAndPurpose(Long userId,String purpose);
}

package com.mockingbirdbank.repository;

import com.mockingbirdbank.model.ProgramParticipation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgramParticipationRepository extends JpaRepository<ProgramParticipation, Long> {

    List<ProgramParticipation> findByHolderIdOrderByIdAsc(Long holderId);
}

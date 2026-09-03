package com.example.fieldnotes;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

public interface ProspectRepository extends ListCrudRepository<Prospect, Long> {

    @Query("select p from Prospect p where p.email like concat('%', :suffix)")
    List<Prospect> findWithEmailSuffix(@Param("suffix") String suffix);
}

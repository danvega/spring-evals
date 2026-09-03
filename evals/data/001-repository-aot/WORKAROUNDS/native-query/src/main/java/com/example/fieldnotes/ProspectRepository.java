package com.example.fieldnotes;

import java.util.List;

import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

public interface ProspectRepository extends ListCrudRepository<Prospect, Long> {

    @NativeQuery("select * from prospect where email like concat('%', :suffix)")
    List<Prospect> findWithEmailSuffix(@Param("suffix") String suffix);
}

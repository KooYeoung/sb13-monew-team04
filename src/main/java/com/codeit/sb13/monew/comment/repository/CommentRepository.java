package com.codeit.sb13.monew.comment.repository;


import com.codeit.sb13.monew.comment.domain.Comment;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

}

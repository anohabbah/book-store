package dev.abbah.bookstore.infra.spi.db.book;

import dev.abbah.bookstore.domain.book.Book;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface BookEntityMapper {

  Book toDomain(BookEntity entity);

  BookEntity toEntity(Book book);

}

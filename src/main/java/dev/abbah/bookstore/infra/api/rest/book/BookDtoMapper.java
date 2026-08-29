package dev.abbah.bookstore.infra.api.rest.book;

import dev.abbah.bookstore.domain.book.Book;
import java.util.Objects;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, imports = Objects.class)
public interface BookDtoMapper {

  @Mapping(target = "id", ignore = true)
  Book toDomain(CreateBookRequest request);

  /**
   * A response only ever describes a persisted book (design D3), so the domain's
   * {@code @Nullable} id narrows to non-null here; a missing one is a programming error.
   */
  @Mapping(
      target = "id",
      expression = "java(Objects.requireNonNull(book.id(), \"a persisted book must have an id\"))")
  BookDto toDto(Book book);

}

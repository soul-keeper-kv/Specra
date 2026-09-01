package dev.specra.api.core.content;

/**
 * What a {@link ContentStore} is actually able to do.
 *
 * <p>Not every backing store can do everything: an imported, read-only archive can be searched but
 * never written to. Declaring the set up front lets {@link ContentStoreRegistry} refuse an
 * unsupported call with a proper problem document, instead of the store throwing halfway through.
 */
public enum ContentCapability {
  READ,
  CREATE,
  UPDATE,
  DELETE
}

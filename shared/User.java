package iti.iwish.shared;

import java.io.Serializable;

public record User(long id, String name, String email) implements Serializable { private static final long serialVersionUID = 1L; }

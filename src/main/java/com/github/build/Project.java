package com.github.build;

import java.util.List;
import java.util.Set;

public interface Project {

    String id();

    Set<Dependency> dependencies();
}

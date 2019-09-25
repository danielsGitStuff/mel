package de.mel.konsole;

import de.mel.Lok;

import java.util.*;
import java.util.stream.Collectors;

public class DependenciesContainer {

    private Map<String, DependencySet> dependencySetMap = new HashMap<>();
    private Set<String> usedAttributes = new HashSet<>();

    public void add(DependencySet dependencySet) {
        dependencySetMap.put(dependencySet.trigger, dependencySet);
    }

    public void onHandleAttribute(String attr) {
        usedAttributes.add(attr);
    }

    public void checkDependencies() throws Konsole.DependenciesViolatedException {
        usedAttributes.forEach(used -> dependencySetMap.computeIfPresent(used, (s, dependencySet) -> {
            dependencySet.fulfilledMap.keySet().forEach(required -> dependencySet.fulfilledMap.put(required, usedAttributes.contains(required)));
            return dependencySet;
        }));
        List<String> violated = dependencySetMap.values().stream().filter(dependencySet -> usedAttributes.contains(dependencySet.trigger) && dependencySet.notFulfilled()).map(DependencySet::getTrigger).sorted().collect(Collectors.toList());
        if (!violated.isEmpty()) {
            Lok.error("dependencies violated");
            for (String attr : violated) {
                Lok.error("'" + attr + "' was set and requires: ");
                List<String> required = dependencySetMap.get(attr).fulfilledMap.keySet().stream().sorted().collect(Collectors.toList());
                for (String r : required)
                    Lok.error(r);
                Lok.error("but '" + attr + "' did not fulfill the following dependencies: ");
                List<String> missing = dependencySetMap.get(attr).fulfilledMap.keySet().stream().filter(a -> !usedAttributes.contains(a)).sorted().collect(Collectors.toList());
                for (String m : missing)
                    Lok.error(m);
            }
            throw new Konsole.DependenciesViolatedException("dependsOn violated");
        }
    }

    public boolean hasDependency(String attr) {
        return dependencySetMap.containsKey(attr);
    }

    public DependencySet getDependencySet(String attr) {
        return dependencySetMap.get(attr);
    }

    public static class DependencySet {
        private Map<String, Boolean> fulfilledMap = new HashMap<>();
        private String trigger;

        public DependencySet setTrigger(String trigger) {
            this.trigger = trigger;
            return this;
        }

        public String getTrigger() {
            return trigger;
        }

        public DependencySet(String... attributes) {
            for (String attribute : attributes) {
                fulfilledMap.put(attribute, false);
            }
        }

        public boolean notFulfilled() {
            return fulfilledMap.values().stream().anyMatch(aBoolean -> !aBoolean);
        }

        public List<String> getDependencies() {
            return fulfilledMap.keySet().stream().sorted().collect(Collectors.toList());
        }
    }
}

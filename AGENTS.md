# Agent Notes

- The project uses Gson for component (rule, indicator, strategy) metadata. Prefer the helper classes in
  `org.ta4j.core.serialization` (see `ComponentDescriptor` and `ComponentSerialization`) instead of hand-rolling JSON when you
  need structured names or serialization glue.
- Composite rule names should be represented as nested component descriptors. Use
  `ComponentSerialization.parse(rule.getName())` to walk existing rule names safely.
- Always finish feature work by running `mvn -B clean license:format formatter:format test install`; the build adds license
  headers and formats code automatically.

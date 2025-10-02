# Agent Notes – Rule Name Feature

## Scope Guardrails
- Keep API changes minimal; prefer default implementations to preserve backwards compatibility.
- Do not alter existing logging or behaviour unless required to support explicit naming.
- Avoid touching files unrelated to rules or strategy composition to keep the PR focused.

## Design Preferences
- Store the name inside `AbstractRule` and expose it via interface defaults.
- Produce JSON-formatted defaults, e.g. {"type":"AndRule"}, so future persistence is trivial.
- Provide helpers for composites to emit structured child lists instead of hand-rolled strings.

## Testing Checklist
- Cover positive and negative cases for set/unset names on AbstractRule.
- Validate composite behaviour with AndRule/OrRule/NotRule where they rely on constructor-injected names.
- Confirm that toString() continues to provide useful output for logging.

## Naming Contract
- Default name derives from the rule's simple class name as a JSON payload.
- Passing null or blank to setName resets the rule name back to its default JSON.

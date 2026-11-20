# Space Why Dep Fix (`space_why_depfix`)

A tiny jar for the [Space Why](https://github.com/mikedaf/Space-Why-Per-Aspera) modpack (Forge 1.20.1). It is not a
normal mod: Forge loads it from the mods folder as an early plugin (an `IDependencyLocator` service) and it loosens one
mod's dependency rule **in memory** while Forge looks at the installed mods. No other mod's jar is changed.

## Why it exists
Zero Point Systems 2.6.x asks for Create: Clockwork 0.5.7 or newer, which was never released. The only Clockwork
code ZPS uses is the same in Clockwork 0.5.6, so Dep Fix lets ZPS accept 0.5.6. It only does anything while
Clockwork 0.5.6 is installed: if Clockwork ever updates to 0.5.7+, this jar does nothing and can be removed.

## Building
`python3 build.py` makes `build/space_why_depfix-1.0.0.jar` (Java 17 classes, compiled against Forge 47.4.10's loader
jars; the script looks for the JDK and those jars in the Prism Launcher folders, edit `JDK` and `LIBS` at its top for
another setup). `python3 build.py --check` says whether the jar is older than the source.

## License
Same as the Space Why pack: CC BY-ND 4.0, see `LICENSE`.

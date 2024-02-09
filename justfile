default:
  @just --choose

test:
  ./mill reflux.lib.test
#  ./mill reflux.generic.test

publish-local:
  mill reflux.lib.publishM2Local
#  mill reflux.generic.publishM2Local

idea:
  ./mill mill.scalalib.GenIdea/idea

#!/bin/sh
# run from top level dir
rm -rf target/docs
git clone git@github.com:jhalterman/recurrent.git target/docs -b gh-pages
mvn -Pjavadoc javadoc:javadoc
cd target/docs
git add -A
git commit -m "Updated JavaDocs"
git push origin gh-pages
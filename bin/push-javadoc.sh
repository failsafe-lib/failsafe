#!/bin/sh
# run from top level dir

PROJECT=failsafe

mvn javadoc:javadoc -Djv=$apiVersion
rm -rf target/docs
git clone git@github.com:jhalterman/$PROJECT.git target/docs -b gh-pages
cd target/docs
git rm -rf javadoc
mkdir -p javadoc
mv -v ../site/apidocs/* javadoc
git add -A -f javadoc
git commit -m "Updated JavaDocs"
git push -fq origin gh-pages > /dev/null

echo "Published JavaDocs"
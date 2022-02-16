#!/bin/sh
# run from top level dir

ORG=failsafe-lib
REPO=failsafe.dev

pwd=`pwd`

build () {
  echo "Building Javadocs for $1"
  cd $pwd
  if [ "$1" != "core" ]; then
    cd modules
  fi
  cd $1
  mvn javadoc:javadoc -Djv=$apiVersion
  rm -rf target/docs
  git clone git@github.com:$ORG/$REPO.git target/docs
  cd target/docs
  git rm -rf javadoc/$1
  mkdir -p javadoc/$1
  mv -v ../site/apidocs/* javadoc/$1

  patchFavIcon "javadoc" "../assets/images/favicon.png"
  commit && echo "Published Javadocs for $1"
}

patchFavIcon () {
  echo "Patching favicons"
  for f in $1/*.html ; do
    if [ -f "$f" ]; then     # if no .html files exist, f is literal "*.html"
      tmpfile=`mktemp patch_favicon_XXXXX`
      # This creates tmpfile, with the same permissions as $f.
      # The next command will overwrite it but preserve the permissions.
      # Hat tip to http://superuser.com/questions/170226/standard-way-to-duplicate-a-files-permissions for this trick.
      \cp -p $f $tmpfile
      sed -e " s%<head>\$%<head><link rel=\"icon\" href=\"$2\" type=\"image/png\"/>%" $f > $tmpfile
      DIFF=$(diff $f $tmpfile) 
      if [ "$DIFF" != "" ] 
      then
          echo "$f modified with favicon"
      fi
      mv -f $tmpfile $f
    fi;
  done ;
  for d in $1/* ; do
    if [ -d $d ]; then echo "descending to "$d ; patchFavIcon $d ../$2 ; fi ;
  done
}

commit() {
  echo "Committing javadocs"
  git add -A -f javadoc
  git commit -m "Updated JavaDocs"
  git push -fq > /dev/null
}

# Install parent and core
echo "Installing parent and core artifacts"
mvn install -N
cd core
mvn install -DskipTests=true
cd ../

build "core"
build "okhttp"
build "retrofit"
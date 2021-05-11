#!/bin/bash

#on terminal write as 
#  ./VelScript.sh -noxml -tool=VD test.Test
# run from rr root folder

while [ 1 ]
do
 rrrun $*
 #echo "want to run again(y/n)?"
 read -p "want to run again(y/n)?" response
 if test "$response" = "n" 
 then
   break
 fi
done

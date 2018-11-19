#!/bin/bash
pkill java
pkill firefox
pkill geckodriver
rm console.log
rm error.log
rm -rf resumes
rm -rf hierarchy_info
nohup gradle run >console.log 2>error.log & 

#!/usr/bin/env bash
#
# Copyright 2016-2017 Red Hat, Inc. and/or its affiliates
# and other contributors as indicated by the @author tags.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# exit codes:
# 0 ok
# if x & 1 == 1 the /status is broken
# if x & 2 == 2 the /metrics/status is broken
# if x & 4 == 4 the /alerts/status is broken
# so for instance return code 7 means that everything is broken

i=1
return_code=0
for path in /status /metrics/status /alerts/status ; do
  http_code=`curl -s -I -o /dev/null -w "%{http_code}" http://$HOSTNAME:8080/hawkular$path`
  [[ "$http_code" -lt "200" || "$http_code" -gt "299" ]] && return_code=$(( $return_code + $i ))
  i=$(( $i << 1 ))
done

exit $return_code

#!/bin/sh
set -eu
bucket="${LOCAL_STORAGE_BUCKET:-backend-images}"
export AWS_DEFAULT_REGION=us-east-1 AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test
if ! awslocal s3api head-bucket --bucket "$bucket" >/dev/null 2>&1; then
  awslocal s3api create-bucket --bucket "$bucket" >/dev/null
fi
awslocal s3api put-public-access-block --bucket "$bucket" --public-access-block-configuration \
  BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
awslocal s3api put-bucket-cors --bucket "$bucket" --cors-configuration \
  '{"CORSRules":[{"AllowedOrigins":["*"],"AllowedMethods":["GET","PUT","POST","DELETE","HEAD"],"AllowedHeaders":["*"],"MaxAgeSeconds":300}]}'

<?php

declare(strict_types=1);

namespace Cocopay\Sdk;

/** 网关返回的业务错误（HTTP 200 且 code=200 之外的情况） */
class ApiException extends \RuntimeException
{
    private int $apiCode;
    private string $apiReason;

    public function __construct(int $code, string $reason, string $message)
    {
        parent::__construct("cocopay: 请求失败 code={$code} reason={$reason} message={$message}");
        $this->apiCode = $code;
        $this->apiReason = $reason;
    }

    public function getApiCode(): int
    {
        return $this->apiCode;
    }

    public function getApiReason(): string
    {
        return $this->apiReason;
    }
}
